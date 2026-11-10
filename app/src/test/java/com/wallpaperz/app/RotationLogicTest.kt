package com.wallpaperz.app

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random
import kotlinx.serialization.json.Json

class RotationLogicTest {
    private val photos = listOf(WallpaperItem("a", "A"), WallpaperItem("b", "B"), WallpaperItem("c", "C"))
    private fun commit(state: CarouselState, next: RotationLogic.Selection) = state.updateGroup(next.groupId) {
        it.copy(currentId = next.id)
    }.copy(playingGroupId = next.groupId, roundPlayedIds = next.playedIds)
    private fun library(mode: PlayMode = PlayMode.ORDERED) = CarouselState(groups = listOf(WallpaperGroup(photos = photos, mode = mode)))

    @Test fun orderedStartsAtFirstAndWraps() {
        var state = library()
        val seen = (1..7).map { val next = RotationLogic.next(state); state = commit(state, next); next.id }
        assertEquals(listOf("a", "b", "c", "a", "b", "c", "a"), seen)
    }

    @Test fun everyModeFinishesEveryRoundWithoutDuplicatesOrAdjacentBoundaryRepeats() {
        PlayMode.entries.forEach { mode ->
            repeat(20) { seed ->
                var state = library(mode)
                val random = Random(seed)
                repeat(20) {
                    val round = (1..photos.size).map {
                        val next = RotationLogic.next(state, random)
                        assertNotEquals(state.playbackGroup.currentId, next.id)
                        state = commit(state, next)
                        next.id
                    }
                    assertEquals(photos.map { it.id }.toSet(), round.toSet())
                    assertEquals(3, state.roundPlayedIds.size)
                }
            }
        }
    }

    @Test fun unevenThemesWaitForTheWholeLibraryInEveryMode() {
        PlayMode.entries.forEach { mode ->
            val groups = listOf(
                WallpaperGroup(photos = photos.take(1), mode = mode),
                WallpaperGroup(id = "large", photos = (1..5).map { WallpaperItem("l$it", "L$it") }, mode = mode),
                WallpaperGroup(id = "medium", photos = (1..2).map { WallpaperItem("m$it", "M$it") }, mode = mode))
            var state = CarouselState(groups = groups, enabledGroupIds = groups.map { it.id }, activeGroupId = "medium")
            val expected = groups.flatMap { it.photos }.map { it.id }.toSet()
            val random = Random(42)
            repeat(20) {
                val round = (1..8).map {
                    val next = RotationLogic.next(state, random)
                    assertNotEquals(state.playbackGroup.currentId, next.id)
                    state = commit(state, next)
                    next.id
                }
                assertEquals(expected, round.toSet())
            }
        }
    }

    @Test fun mixedThemesKeepTheirOrderWhileSkippingFinishedThemes() {
        val groups = listOf(WallpaperGroup(photos = photos.take(2)), WallpaperGroup(id = "other", photos = listOf(photos[2])))
        var state = CarouselState(groups = groups, enabledGroupIds = groups.map { it.id })
        val seen = (1..6).map { val next = RotationLogic.next(state); state = commit(state, next); next.id }
        assertEquals(listOf("a", "c", "b", "c", "a", "b"), seen)
    }

    @Test fun editingThemeNeverChangesWhichThemeIsNext() {
        val other = WallpaperGroup(id = "other", photos = listOf(WallpaperItem("x", "X")))
        val state = library().copy(groups = library().groups + other, activeGroupId = "other")
        assertEquals("default", RotationLogic.next(state).groupId)
        assertEquals("a", RotationLogic.next(state).id)
    }

    @Test fun uncheckedAndEmptyThemesDoNotParticipate() {
        val state = library().copy(groups = library().groups + WallpaperGroup(id = "empty") + WallpaperGroup(id = "off", photos = listOf(WallpaperItem("x", "X"))), enabledGroupIds = listOf("default", "empty"))
        assertEquals("default", RotationLogic.next(state).groupId)
        assertEquals("a", RotationLogic.next(state).id)
    }

    @Test fun singleItemRepeatsOnlyBecauseThereIsNoAlternative() {
        PlayMode.entries.forEach { mode ->
            var state = CarouselState(groups = listOf(WallpaperGroup(photos = photos.take(1), mode = mode)))
            repeat(4) { val next = RotationLogic.next(state); assertEquals("a", next.id); state = commit(state, next) }
        }
    }

    @Test(expected = IllegalStateException::class) fun emptyLibraryCannotAdvance() { RotationLogic.next(CarouselState()) }
    @Test(expected = IllegalStateException::class) fun noCheckedThemesCannotAdvance() { RotationLogic.next(library().copy(enabledGroupIds = emptyList())) }

    @Test fun newOrderingDoesNotReplayAlreadySeenItems() {
        val state = library().updateGroup { it.copy(photos = photos.reversed(), currentId = "b") }.copy(roundPlayedIds = listOf("a", "b"), playingGroupId = "default")
        assertEquals("c", RotationLogic.next(state).id)
    }

    @Test fun changingModeDoesNotEraseRoundHistory() {
        PlayMode.entries.forEach { mode ->
            val state = library(mode).copy(roundPlayedIds = listOf("a", "c"))
            assertEquals("b", RotationLogic.next(state).id)
        }
    }

    @Test fun addingDeletingMovingAndEnablingThemesUseTheSameHistory() {
        var state = library().copy(roundPlayedIds = listOf("a", "b"), playingGroupId = "default")
        val d = WallpaperItem("d", "D")
        state = state.updateGroup { it.copy(photos = listOf(photos[0], photos[2], d)) }
        assertEquals("c", RotationLogic.next(state).id)
        state = commit(state, RotationLogic.next(state))
        assertEquals("d", RotationLogic.next(state).id)
        val moved = WallpaperGroup(id = "moved", photos = listOf(photos[0], d))
        state = state.updateGroup { it.copy(photos = listOf(photos[2])) }.copy(groups = listOf(state.group.copy(photos = listOf(photos[2])), moved), enabledGroupIds = listOf("default", "moved"))
        assertEquals("d", RotationLogic.next(state).id)
    }

    @Test fun manualSelectionMarksTheItemAsPlayedButAllowsExplicitSelection() {
        var state = library()
        state = commit(state, RotationLogic.select(state, "b"))
        assertEquals(listOf("b"), state.roundPlayedIds)
        assertEquals("c", RotationLogic.next(state).id)
        val explicit = RotationLogic.select(state, "b")
        assertEquals("b", explicit.id)
        assertEquals(listOf("b"), explicit.playedIds)
    }

    @Test fun newImportsAreMoreLikelyEarlyButAppearOnlyOnceInTheRound() {
        val now = 1700000000000L
        val fresh = WallpaperItem("new", "New", importedAt = now)
        val old = WallpaperItem("old", "Old")
        val state = CarouselState(groups = listOf(WallpaperGroup(photos = listOf(fresh, old), mode = PlayMode.FAVOR_NEW)))
        val random = Random(42)
        val firsts = (1..10000).map { RotationLogic.next(state, random, now).id }
        assertTrue(firsts.count { it == "new" } in 7700..8300)
        var advanced = commit(state, RotationLogic.next(state, Random(1), now))
        val other = RotationLogic.next(advanced, random, now)
        assertNotEquals(advanced.playbackGroup.currentId, other.id)
        advanced = commit(advanced, other)
        assertEquals(setOf("new", "old"), advanced.roundPlayedIds.toSet())
    }

    @Test fun newnessWeightDecaysWithoutDependingOnManualOrder() {
        val now = 1700000000000L
        val fresh = WallpaperItem("new", "New", importedAt = now)
        assertEquals(4.0, RotationLogic.newnessWeight(fresh, now), 0.0001)
        assertEquals(2.5, RotationLogic.newnessWeight(fresh, now + 302400000L), 0.0001)
        assertEquals(1.0, RotationLogic.newnessWeight(fresh, now + 604800000L), 0.0001)
        assertEquals(1.0, RotationLogic.newnessWeight(photos.first(), now), 0.0001)
    }

    @Test fun processRestartPreservesTheWholeRoundAndSettings() {
        val state = library(PlayMode.FAVOR_NEW).updateGroup { it.copy(intervalSeconds = 300, trigger = Trigger.INTERVAL, currentId = "b") }
            .copy(playingGroupId = "default", roundPlayedIds = listOf("a", "b"), automatic = true)
        val restored = decodeCarouselState(Json.encodeToString(state))
        assertEquals(state, restored)
        assertEquals("c", RotationLogic.next(restored).id)
    }

    @Test fun oldThreeSecondAndPerThemeQueueAreRemovedOnUpgrade() {
        val state = decodeCarouselState("""{"groups":[{"photos":[{"id":"a","name":"A"},{"id":"b","name":"B"}],"currentId":"a","remaining":["b"],"intervalSeconds":3,"mode":"FAVOR_NEW"}],"playingGroupId":"default"}""")
        assertEquals(60, state.intervalSeconds)
        assertEquals(listOf("a"), state.roundPlayedIds)
        assertEquals("b", RotationLogic.next(state).id)
        val encoded = Json.encodeToString(state)
        assertFalse(encoded.contains("remaining"))
        assertEquals(state, decodeCarouselState(encoded))
    }

    @Test fun oldMinuteSettingsAndFlatLibraryConvertWithoutLosingMedia() {
        val state = decodeCarouselState("""{"photos":[{"id":"a","name":"A"}],"currentId":"a","intervalMinutes":15,"remaining":[],"automatic":true}""")
        assertEquals(900, state.intervalSeconds)
        assertEquals(listOf("a"), state.roundPlayedIds)
        assertTrue(state.automatic)
        assertEquals("a", state.photos.single().id)
    }

    @Test fun removedThreeSecondOptionIsNotExposed() {
        assertFalse(3 in INTERVALS)
        assertEquals(60, INTERVALS.first())
        assertEquals("1 分钟", intervalLabel(60))
        assertEquals("24 小时", intervalLabel(86400))
    }

    @Test fun defaultsAndEmptyUncheckedLibraryRoundTrip() {
        val state = CarouselState(enabledGroupIds = emptyList())
        assertEquals(state, decodeCarouselState(Json.encodeToString(state)))
        assertFalse(state.automatic)
        assertEquals(Trigger.UNLOCK, state.trigger)
        assertEquals(60, state.intervalSeconds)
    }
}
