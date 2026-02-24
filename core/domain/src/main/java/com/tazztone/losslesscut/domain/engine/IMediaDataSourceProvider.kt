package com.tazztone.losslesscut.domain.engine

import java.io.FileDescriptor

public interface IMediaDataSourceProvider {
    public fun openFileDescriptor(uriString: String, mode: String): FileDescriptor?
}
