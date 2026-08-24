package com.weave.core

public interface Reducer<S, U> {
    public fun reduce(state: S, update: U): S

    public fun merge(left: S, right: S): S
}
