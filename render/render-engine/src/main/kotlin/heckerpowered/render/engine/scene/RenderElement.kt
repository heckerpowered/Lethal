/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 heckerpowered
 */

package heckerpowered.render.engine.scene

/**
 * Describes something to render before a pass chooses its GPU commands.
 *
 * For example, [GeometryElement] pairs local-space geometry with its appearance. The same element
 * can be submitted at several placements or processed for several views. [RenderSubmission]
 * records each occurrence; the element itself does not select a pass or capture placement.
 *
 * The selected pass processor must support the concrete element type. This interface does not
 * make arbitrary elements drawable or copy their contents; retain stable rendering data for as
 * long as submissions may be processed.
 */
interface RenderElement
