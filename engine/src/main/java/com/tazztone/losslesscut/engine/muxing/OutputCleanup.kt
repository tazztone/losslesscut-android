package com.tazztone.losslesscut.engine.muxing

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/** Removes a partial or failed output file regardless of which provider backs its URI. */
internal object OutputCleanup {
    fun delete(context: Context, uri: Uri): Boolean =
        if (DocumentsContract.isDocumentUri(context, uri)) {
            // Custom output folders are DocumentsProvider URIs; ContentResolver.delete is
            // unsupported there and would leave the partial file behind.
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        } else {
            context.contentResolver.delete(uri, null, null) > 0
        }
}
