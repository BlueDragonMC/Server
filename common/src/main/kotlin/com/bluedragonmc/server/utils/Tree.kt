package com.bluedragonmc.server.utils

class Root<T> : Node<T>(null)

open class Node<T>(var value: T?) {

    private val children = mutableListOf<Node<T>>()
    lateinit var parent: Node<T>

    fun getChildren(): List<Node<T>> = children
    fun getSiblings(): List<Node<T>> = parent.children

    fun addChild(element: T) {
        val node = Node(element)
        node.parent = this
        children.add(node)
    }

    fun addChild(node: Node<T>) {
        node.parent = this
        children.add(node)
    }

    fun addChildren(elements: Collection<T>) {
        elements.forEach { node -> addChild(node) }
    }

    fun maxDepth(): Int {
        return if (children.isEmpty()) 0
        else 1 + children.maxOf { it.maxDepth() }
    }

    fun elementsAtDepth(depth: Int): List<Node<T>> {
        val list = mutableListOf<Node<T>>()
        dfs { node, i -> if (i == depth) list.add(node) }
        return list
    }

    fun dfs(depth: Int = 0, visitor: (Node<T>, Int) -> Unit) {
        visitor(this, depth)
        ArrayList(children).forEach {
            it.dfs(depth + 1, visitor)
        }
    }

    fun removeChild(element: T) {
        children.removeIf { it.value == element }
    }

    private fun removeChild(element: Node<T>) {
        children.remove(element)
    }

    fun removeFromParent() = parent.removeChild(this)

    private val symbols = listOf("", "-", ">")

    override fun toString(): String {
        if (children.isEmpty()) {
            return this.value.toString()
        }
        return buildString {
            append("Tree ${this@Node::class.simpleName}@${this.hashCode()}:\n")
            dfs { node, depth ->
                if (node is Root) return@dfs
                append("${"\t".repeat(depth)} ${symbols[depth % symbols.size]} ${node.value}\n")
            }
        }
    }
}
