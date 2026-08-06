package com.usage.claudewidget.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.usage.claudewidget.work.RefreshScheduler

/** Tap action when the widget is healthy: kick off an immediate refresh. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        RefreshScheduler.refreshNow(context)
    }
}

/** Tap on a usage bar: open the Claude app's Usage screen (browser fallback). */
class OpenClaudeUsageAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val view = Intent(Intent.ACTION_VIEW, Uri.parse("https://claude.ai/settings/usage"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(Intent(view).setPackage(CLAUDE_PACKAGE))
        } catch (_: Exception) {
            val fallback = context.packageManager.getLaunchIntentForPackage(CLAUDE_PACKAGE) ?: view
            try {
                context.startActivity(fallback)
            } catch (_: Exception) {
                // No Claude app and no browser; nothing sensible to do.
            }
        }
    }

    companion object {
        private const val CLAUDE_PACKAGE = "com.anthropic.claude"
    }
}

/** Shortcut chip: launch the Claude app (claude.ai in the browser as fallback). */
class OpenClaudeAppAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val intent = context.packageManager.getLaunchIntentForPackage("com.anthropic.claude")
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://claude.ai"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }
}

/** Shortcut chip: open a URL in the Claude app, falling back to the browser. */
class OpenLinkAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val url = parameters[URL] ?: return
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            // Explicit package routes into the Claude app even when it isn't
            // the verified default handler for claude.ai links.
            context.startActivity(Intent(view).setPackage("com.anthropic.claude"))
        } catch (_: Exception) {
            try {
                context.startActivity(view)
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        val URL = ActionParameters.Key<String>("url")
    }
}
