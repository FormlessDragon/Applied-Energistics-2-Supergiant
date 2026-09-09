/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package ae2.me.service;

import ae2.api.networking.ticking.TickingRequest;
import ae2.me.service.helpers.TickTracker;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentityIndexedHeapTest {

    private static final Comparator<Entry> ORDER = Comparator.comparingInt(Entry::priority);

    @Test
    void keepsDifferentObjectsWithEqualOrderingKeys() {
        IdentityIndexedHeap<Entry> heap = new IdentityIndexedHeap<>(ORDER);
        Entry first = new Entry(5);
        Entry second = new Entry(5);

        heap.add(first);
        heap.add(second);

        assertEquals(2, heap.size());
        assertTrue(heap.contains(first));
        assertTrue(heap.contains(second));
        Entry firstPolled = heap.poll();
        Entry secondPolled = heap.poll();
        assertNotSame(firstPolled, secondPolled);
        assertTrue(firstPolled == first || firstPolled == second);
        assertTrue(secondPolled == first || secondPolled == second);
        assertTrue(heap.isEmpty());
    }

    @Test
    void removesArbitraryElementsAndMaintainsMembership() {
        IdentityIndexedHeap<Entry> heap = new IdentityIndexedHeap<>(ORDER);
        Entry first = new Entry(1);
        Entry removed = new Entry(2);
        Entry third = new Entry(3);
        Entry fourth = new Entry(4);
        heap.add(fourth);
        heap.add(removed);
        heap.add(third);
        heap.add(first);

        assertTrue(heap.remove(removed));
        assertFalse(heap.contains(removed));
        assertFalse(heap.remove(removed));
        assertSame(first, heap.poll());
        assertSame(third, heap.poll());
        assertSame(fourth, heap.poll());
        assertNull(heap.poll());
    }

    @Test
    void updatesPositionsInBothDirections() {
        IdentityIndexedHeap<Entry> heap = new IdentityIndexedHeap<>(ORDER);
        Entry first = new Entry(10);
        Entry second = new Entry(20);
        Entry third = new Entry(30);
        heap.add(first);
        heap.add(second);
        heap.add(third);

        third.priority = 1;
        heap.updatePosition(third);
        assertSame(third, heap.peek());

        third.priority = 40;
        heap.updatePosition(third);
        assertSame(first, heap.poll());
        assertSame(second, heap.poll());
        assertSame(third, heap.poll());
    }

    @Test
    void rejectsDuplicateIdentityAndUpdatingAbsentElements() {
        IdentityIndexedHeap<Entry> heap = new IdentityIndexedHeap<>(ORDER);
        Entry entry = new Entry(1);

        heap.add(entry);
        assertThrows(IllegalArgumentException.class, () -> heap.add(entry));
        assertTrue(heap.remove(entry));
        assertFalse(heap.contains(entry));
        assertThrows(IllegalArgumentException.class, () -> heap.updatePosition(entry));
    }

    @Test
    void polledObjectCanBeReinsertedAndReordered() {
        IdentityIndexedHeap<Entry> heap = new IdentityIndexedHeap<>(ORDER);
        Entry first = new Entry(1);
        Entry second = new Entry(2);
        heap.add(first);
        heap.add(second);

        assertSame(first, heap.poll());
        first.priority = 3;
        heap.add(first);
        second.priority = 4;
        heap.updatePosition(second);

        assertSame(first, heap.poll());
        assertSame(second, heap.poll());
    }

    @Test
    void foreignQueueCannotRemoveOrUpdateTrackerAndMigrationReleasesItsSlot() {
        var source = new IdentityIndexedHeap<>(ORDER);
        var destination = new IdentityIndexedHeap<>(ORDER);
        var first = new Entry(1);
        var second = new Entry(1);
        source.add(first);
        destination.add(second);
        assertFalse(destination.contains(first));
        assertFalse(destination.remove(first));
        assertThrows(IllegalArgumentException.class, () -> destination.updatePosition(first));
        assertThrows(IllegalArgumentException.class, () -> destination.add(first));
        assertSame(first, source.poll());
        assertEquals(-1, first.getHeapIndex());
        destination.add(first);
        assertTrue(destination.contains(first));
        assertFalse(source.remove(first));
        assertTrue(destination.remove(first));
        assertEquals(-1, first.getHeapIndex());
        assertSame(second, destination.poll());
    }

    @Test
    void randomUpdatesRemovalsAndReinsertionsMatchMinimumAndMembership() {
        var heap = new IdentityIndexedHeap<>(ORDER);
        var entries = new ArrayList<Entry>();
        var random = new Random(87234);
        for (int i = 0; i < 256; i++) {
            var entry = new Entry(random.nextInt(100));
            entries.add(entry);
            heap.add(entry);
        }
        for (int step = 0; step < 10000; step++) {
            var entry = entries.get(random.nextInt(entries.size()));
            if (entry.getHeapIndex() == -1) {
                heap.add(entry);
            } else if (random.nextBoolean()) {
                assertTrue(heap.remove(entry));
                assertEquals(-1, entry.getHeapIndex());
            } else {
                entry.priority = random.nextInt(100);
                heap.updatePosition(entry);
            }
            int minimum = Integer.MAX_VALUE;
            int count = 0;
            for (int i = 0; i < entries.size(); i++) {
                var candidate = entries.get(i);
                assertEquals(candidate.getHeapIndex() >= 0, heap.contains(candidate));
                if (candidate.getHeapIndex() >= 0) {
                    minimum = Math.min(minimum, candidate.priority);
                    count++;
                }
            }
            assertEquals(count, heap.size());
            assertEquals(minimum, heap.peek().priority);
            if (step % 10 == 0) {
                var polled = heap.poll();
                assertEquals(minimum, polled.priority);
                assertEquals(-1, polled.getHeapIndex());
                polled.priority = random.nextInt(100);
                heap.add(polled);
            }
        }
    }

    private static final class Entry extends TickTracker {

        private int priority;

        private Entry(int priority) {
            super(new TickingRequest(1, 20, false), null, null, 0);
            this.priority = priority;
        }

        private int priority() {
            return this.priority;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Entry;
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }
}
