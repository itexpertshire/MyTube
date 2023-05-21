package com.github.libretube.constants



/**
 * API link for the update checker
 */
const val GITHUB_API_URL = "https://api.github.com/repos/libre-tube/LibreTube/releases/latest"

/**
 * Links for the about fragment
 */
const val WEBSITE_URL = "https://libre-tube.github.io/"
const val GITHUB_URL = "https://github.com/libre-tube/LibreTube"
const val PIPED_GITHUB_URL = "https://github.com/TeamPiped/Piped"
const val WEBLATE_URL = "https://hosted.weblate.org/projects/libretube/libretube/"
const val LICENSE_URL = "https://gnu.org/"
const val FAQ_URL = "https://libre-tube.github.io/#faq"

/**
 * Social media links for the community fragment
 */
const val MATRIX_URL = "https://matrix.to/#/#LibreTube:matrix.org"
const val MASTODON_URL = "https://fosstodon.org/@libretube"
const val TELEGRAM_URL = "https://t.me/libretube"

/**
 * Share Dialog
 */
const val PIPED_FRONTEND_URL = "https://piped.video"
const val YOUTUBE_FRONTEND_URL = "https://www.youtube.com"

/**
 * Retrofit Instance
 */
const val PIPED_API_URL = "https://pipedapi.kavin.rocks"
const val PIPED_INSTANCES_URL = "https://piped-instances.kavin.rocks"
const val FALLBACK_INSTANCES_URL = "https://instances.tokhmi.xyz"

/**
 * Notification IDs
 */
const val PLAYER_NOTIFICATION_ID = 1
const val DOWNLOAD_PROGRESS_NOTIFICATION_ID = 2

/**
 * Notification Channel IDs
 */
const val DOWNLOAD_CHANNEL_ID = "download_service"
const val BACKGROUND_CHANNEL_ID = "background_mode"
const val PUSH_CHANNEL_ID = "notification_worker"

/**
 * Database
 */
const val DATABASE_NAME = "LibreTubeDatabase"

/**
 * New Streams notifications
 */
const val NOTIFICATION_WORK_NAME = "NotificationService"


/**
 * Id of recommendation job
 */

const val RECOMMENDATION_JOB_ID = 9911
const val RECOMMENDATION_JOB_STATUS = "rec_job_status"
const val RECOMMENDATION_JOB_INTERVAL = 15
const val KEYWORD_HISTORY_SIZE = 100
const val RECOMMENDATION_VIDEO_MAX_CNT = 100
const val RECOMMENDATION_PER_KEYWORD_MAX_CNT = 5
const val FEATURED_VIDEO_MAX_CNT = 50
