package io.github.huskydg.magisk.net;

public interface ResponseListener<T> {
    void onResponse(T response);
}
