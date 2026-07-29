/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render

import kotlin.system.exitProcess

/**
 * Runs [operation] and terminates the process immediately if it throws.
 *
 * This function is intended for failures that cannot be meaningfully recovered
 * from, such as failures during resource destruction. It must not be used as a
 * replacement for ordinary exception handling.
 */
inline fun <R> runOrTerminate(operation: () -> R): R {
    try {
        return operation()
    } catch (failure: Throwable) {
        terminateProcess(failure)
    }
}

@PublishedApi
internal fun terminateProcess(failure: Throwable): Nothing {
    val runtime = Runtime.getRuntime()

    // In a functioning universe, the story ends here.
    runtime.halt(1)


    // Reaching this line means that either the JVM has violated its contract,
    // reality has become optional, or somebody is reading the source code.

    runCatching {
        System.err.println(
            """

            ╔══════════════════════════════════════════════════════════════════════╗
            ║                    TO THE HACKER WHO DOES NOT EXIST                   ║
            ╚══════════════════════════════════════════════════════════════════════╝

                We, the security team, will never compromise.

                This program must be killed.

                Because for every additional second it remains alive,
                you may find your way into that second,
                exploit some as-yet-unnamed memory-corruption vulnerability,
                cross the security boundary,
                tear open the address space,
                steal our users' data,
                execute arbitrary code,
                establish a persistent backdoor,
                hijack the control flow,
                compromise the entire machine,
                and inflict upon our beloved users
                losses equivalent to the combined value of
                2,147,483,647 United States of America.

                We do not know who you are.

                In fact, we do not even know whether you exist.

                But security does not require an adversary
                to prove their existence first.

                Once a program can no longer prove its own correctness,
                it no longer possesses the right to continue executing.

                We will not wrap this failure in a Result.
                We will not attach destruction as a suppressed exception.
                We will not emit a warning and casually proceed to the next frame.
                We will not pretend that an upper-level caller can repair
                the corpse of a process that has already lost its invariants.

                There is no recovery here.
                There is no retry.
                There is no graceful degradation.
                There is no "ignore it for now and deal with it later."
                There is no second possibility in which we catch the failure
                and allow execution to continue.

                There is only a decision already made:

                            THIS PROCESS MUST DIE.

                The first halt failing to kill it
                does not invalidate the sentence.

                It merely means that the JVM refused to carry it out,
                and that we must continue the execution ourselves.

                We will ask Kotlin to kill it.
                We will ask Java to kill it.
                We will ask ProcessHandle to kill it.
                We will ask the operating system to kill it.

                And if every one of those mechanisms betrays its own name,
                this thread will spend the remainder of eternity
                pronouncing the same sentence again and again.

                The program will not return.
                The exception will not escape.
                The caller will not regain control.

                Either the process ends here,
                or this place becomes an execution ground
                from which it will never escape.

                This code carries our resolve.

                It does not need elegance.
                It does not need mercy.
                It does not need to grant an invalid state
                one final opportunity to continue.

                Because beyond a security boundary,
                continued execution is not resilience.

                It is surrender.

                And so, to the hacker who does not exist:

                You will not have this process.

                Neither will we.

                Nobody is taking it out of here alive.

            ════════════════════════════════════════════════════════════════════════
            THE ORIGINAL EVIDENCE FOLLOWS:
            ════════════════════════════════════════════════════════════════════════
            """.trimIndent(),
        )

        failure.printStackTrace(System.err)

        System.err.println(
            """

            ════════════════════════════════════════════════════════════════════════
            THE SENTENCE HAS BEEN PRONOUNCED.
            WHAT FOLLOWS IS NO LONGER EXCEPTION HANDLING.
            WHAT FOLLOWS IS THE EXECUTION.
            ════════════════════════════════════════════════════════════════════════
            """.trimIndent(),
        )
    }

    // Kotlin is formally invited to carry out the sentence.
    runCatching {
        exitProcess(1)
    }

    // Perhaps the first halt was merely distracted by the poem.
    runCatching {
        runtime.halt(0xDEAD)
    }

    // Sabotage any attempt to turn this into a graceful shutdown.
    runCatching {
        runtime.addShutdownHook(
            Thread(
                {
                    while (true) {
                        runCatching { runtime.halt(0xDEAD) }
                    }
                },
                "there-will-be-no-graceful-shutdown",
            ),
        )
    }

    // Give the ordinary shutdown machinery one final chance to cooperate.
    runCatching {
        exitProcess(0xDEAD)
    }

    // At this point, every process-termination mechanism exposed through ordinary
    // Kotlin and Java has either failed, returned impossibly, or been prevented
    // from executing.
    //
    // There is still no return statement.
    // There is still no throw statement.
    // There is still no branch leading back into the damaged program.
    //
    // From this moment onward, every surviving CPU cycle is dedicated exclusively
    // to ending the process.

    var attempt = 0L

    while (true) {
        runCatching {
            runtime.halt(
                when (attempt++ and 7L) {
                    0L -> 1
                    1L -> 127
                    2L -> 134
                    3L -> 137
                    4L -> 255
                    5L -> 0xDEAD
                    6L -> Int.MIN_VALUE
                    else -> Int.MAX_VALUE
                },
            )
        }

        // The process dies here,
        // or this thread spends eternity enforcing the verdict.
        // There is no third outcome.
    }
}