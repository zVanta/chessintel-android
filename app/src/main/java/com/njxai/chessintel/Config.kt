package com.njxai.chessintel

/**
 * Connection settings for the ChessIntel agent hosted on LibreChat.
 *
 * Change [BASE_URL] and [AGENT_ID] if you deploy on a different domain
 * or register a different agent.
 */
object Config {
    const val BASE_URL = "https://njxai.com"

    // The LibreChat agent id (agents.id field in MongoDB), not the Mongo _id.
    const val AGENT_ID = "agent_oyvghVijlzhuGXazjSQqv"

    // LibreChat's user-agent middleware rejects non-browser clients ("Illegal request"),
    // so every request must look like a browser.
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
}
