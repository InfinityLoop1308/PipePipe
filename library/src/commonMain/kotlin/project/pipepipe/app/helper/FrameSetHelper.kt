package project.pipepipe.app.helper

import project.pipepipe.shared.infoitem.helper.stream.Frameset

/**
 * Returns the information for the frame at stream position.
 *
 * @param position Position in milliseconds
 * @return An `IntArray` containing the bounds and URL where the indexes are:
 * - 0: Index of the URL
 * - 1: Left bound
 * - 2: Top bound
 * - 3: Right bound
 * - 4: Bottom bound
 */
fun Frameset.getFrameBoundsAt(position: Long): IntArray {
    if (position < 0 || position > ((totalCount + 1).toLong() * durationPerFrame)) {
        // Return the first frame as fallback
        return intArrayOf(0, 0, 0, frameWidth, frameHeight)
    }

    val framesPerStoryboard = framesPerPageX * framesPerPageY
    val absoluteFrameNumber = minOf((position / durationPerFrame).toInt(), totalCount)
    val relativeFrameNumber = absoluteFrameNumber % framesPerStoryboard

    val rowIndex = relativeFrameNumber / framesPerPageX
    val columnIndex = relativeFrameNumber % framesPerPageX

    return intArrayOf(
        absoluteFrameNumber / framesPerStoryboard,  // storyboardIndex
        columnIndex * frameWidth,                  // left
        rowIndex * frameHeight,                    // top
        columnIndex * frameWidth + frameWidth,     // right
        rowIndex * frameHeight + frameHeight       // bottom
    )
}