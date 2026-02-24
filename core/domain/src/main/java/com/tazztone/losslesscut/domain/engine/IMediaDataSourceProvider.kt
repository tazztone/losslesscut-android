package com.tazztone.losslesscut.domain.engine

import java.io.FileDescriptor

interface IMediaDataSourceProvider {
    fun openFileDescriptor(uriString: String, mode: String): FileDescriptor?
}
