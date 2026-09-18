package com.eventb.checker

object TestStackHelper {

    /**
     * Small enough that a few hundred nesting levels overflow it, large enough for everything
     * else a test does on the way there (zip reads, DOM parses, assertions).
     */
    const val SMALL_STACK_BYTES = 256L * 1024

    /**
     * Runs [block] on a thread with a fixed stack, so a depth-sensitive test does not depend on
     * the Gradle test worker's stack size. The build sets no `-Xss`, so a worker with a large
     * stack would silently stop exercising the overflow and the test would pass for the wrong
     * reason; pinning the stack pins the threshold instead. Rodin's parser burns roughly 500-1000
     * bytes per nesting level, so at 256 KB the edge is around 300-500 levels and the depths these
     * tests use clear it by an order of magnitude.
     *
     * Anything the block throws is rethrown here, `Error` included — a guard that stops working
     * therefore fails the test rather than vanishing into a dead thread.
     */
    fun <T> onSmallStack(stackBytes: Long = SMALL_STACK_BYTES, block: () -> T): T {
        var result: Result<T>? = null
        val thread = Thread(null, { result = runCatching(block) }, "test-small-stack", stackBytes)
        thread.start()
        thread.join()
        return checkNotNull(result) { "thread produced no result" }.getOrThrow()
    }

    /**
     * Nesting depth for a formula that must overflow. Rodin's parser burns roughly 500-1000 bytes
     * per level, so a [SMALL_STACK_BYTES] stack gives out somewhere around 300-500 levels; this
     * clears that by an order of magnitude on any JVM.
     */
    const val DEEP = 5000

    /** `1` wrapped in [depth] pairs of parentheses — nesting that collapses to no AST at all. */
    fun nested(depth: Int): String = "(".repeat(depth) + "1" + ")".repeat(depth)

    /** [predicate] behind [depth] negations — nesting that survives parsing as a real AST. */
    fun negated(depth: Int, predicate: String): String = "¬".repeat(depth) + predicate
}
