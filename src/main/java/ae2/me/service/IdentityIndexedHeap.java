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

import ae2.me.service.helpers.TickTracker;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.Comparator;
import java.util.Objects;

/**
 * A binary min-heap that tracks elements by identity so arbitrary removal and priority changes remain
 * logarithmic. Positions live in the trackers, so sifting does not access a hash table.
 * Comparator-equal elements remain distinct as long as they are different objects.
 */
final class IdentityIndexedHeap<E extends TickTracker> {

    private static final int NOT_PRESENT = -1;

    private final ObjectArrayList<E> elements = new ObjectArrayList<>();
    private final Comparator<? super E> comparator;

    IdentityIndexedHeap(Comparator<? super E> comparator) {
        this.comparator = Objects.requireNonNull(comparator, "comparator");
    }

    private static int parentIndex(int index) {
        return (index - 1) >>> 1;
    }

    private static int leftChildIndex(int index) {
        return (index << 1) + 1;
    }

    boolean isEmpty() {
        return this.elements.isEmpty();
    }

    int size() {
        return this.elements.size();
    }

    boolean contains(E element) {
        int index = element.getHeapIndex();
        return index >= 0 && index < this.elements.size() && this.elements.get(index) == element;
    }

    E peek() {
        return this.elements.isEmpty() ? null : this.elements.getFirst();
    }

    void add(E element) {
        Objects.requireNonNull(element, "element");
        if (element.getHeapIndex() != NOT_PRESENT) {
            throw new IllegalArgumentException("The tracker is already present in a heap");
        }

        int index = this.elements.size();
        this.elements.add(element);
        element.setHeapIndex(index);
        this.siftUp(index);
    }

    E poll() {
        if (this.elements.isEmpty()) {
            return null;
        }

        E result = this.elements.getFirst();
        this.removeAt(0, result);
        return result;
    }

    boolean remove(E element) {
        if (!this.contains(element)) {
            return false;
        }

        this.removeAt(element.getHeapIndex(), element);
        return true;
    }

    void updatePosition(E element) {
        if (!this.contains(element)) {
            throw new IllegalArgumentException("Cannot update an object that is not present in this heap");
        }

        int index = element.getHeapIndex();
        int parentIndex = parentIndex(index);
        if (index > 0 && this.compare(index, parentIndex) < 0) {
            this.siftUp(index);
        } else {
            this.siftDown(index);
        }
    }

    private void removeAt(int index, E removedElement) {
        int lastIndex = this.elements.size() - 1;
        E lastElement = this.elements.remove(lastIndex);
        removedElement.setHeapIndex(NOT_PRESENT);

        if (index == lastIndex) {
            return;
        }

        this.elements.set(index, lastElement);
        lastElement.setHeapIndex(index);

        int parentIndex = parentIndex(index);
        if (index > 0 && this.compare(index, parentIndex) < 0) {
            this.siftUp(index);
        } else {
            this.siftDown(index);
        }
    }

    private void siftUp(int index) {
        E element = this.elements.get(index);
        while (index > 0) {
            int parentIndex = parentIndex(index);
            E parent = this.elements.get(parentIndex);
            if (this.comparator.compare(element, parent) >= 0) {
                break;
            }

            this.elements.set(index, parent);
            parent.setHeapIndex(index);
            index = parentIndex;
        }

        this.elements.set(index, element);
        element.setHeapIndex(index);
    }

    private void siftDown(int index) {
        int size = this.elements.size();
        E element = this.elements.get(index);
        int half = size >>> 1;
        while (index < half) {
            int childIndex = leftChildIndex(index);
            E child = this.elements.get(childIndex);
            int rightChildIndex = childIndex + 1;
            if (rightChildIndex < size
                && this.comparator.compare(this.elements.get(rightChildIndex), child) < 0) {
                childIndex = rightChildIndex;
                child = this.elements.get(childIndex);
            }
            if (this.comparator.compare(element, child) <= 0) {
                break;
            }

            this.elements.set(index, child);
            child.setHeapIndex(index);
            index = childIndex;
        }

        this.elements.set(index, element);
        element.setHeapIndex(index);
    }

    private int compare(int leftIndex, int rightIndex) {
        return this.comparator.compare(this.elements.get(leftIndex), this.elements.get(rightIndex));
    }
}
