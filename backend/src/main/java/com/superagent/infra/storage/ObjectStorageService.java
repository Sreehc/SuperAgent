package com.superagent.infra.storage;

import java.io.InputStream;

public interface ObjectStorageService {

    StoredObject store(
            String objectKey,
            InputStream inputStream,
            long size,
            String contentType
    );

    record StoredObject(String bucket, String objectKey) {
    }
}
