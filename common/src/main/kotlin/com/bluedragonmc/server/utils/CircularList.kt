package com.bluedragonmc.server.utils

class CircularList<out T>(private val list: List<T>) : List<T> by list {
    override fun get(index: Int): T {
        if (isEmpty()) {
            throw NoSuchElementException("List is empty.")
        } else {
            return list[index % size]
        }
    }
}