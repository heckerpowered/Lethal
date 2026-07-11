/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.lethal.platform.services

import heckerpowered.bridge.platform.services.PlatformLogger
import heckerpowered.lethal.Constants
import org.apache.logging.log4j.LogManager

class ForgePlatformLogger : PlatformLogger {
    private val delegate = LogManager.getLogger(Constants.MOD_ID)

    override fun debug(message: String) {
        delegate.debug(message)
    }

    override fun info(message: String) {
        delegate.info(message)
    }

    override fun warn(message: String) {
        delegate.warn(message)
    }

    override fun error(message: String, throwable: Throwable?) {
        delegate.error(message, throwable)
    }
}
