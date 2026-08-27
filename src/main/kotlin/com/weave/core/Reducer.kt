package com.weave.core

public interface Reducer<S, U> {
    public fun reduce(state: S, update: U): S

    public fun merge(left: S, right: S): S

    public fun merge(first: S, vararg states: S): S {
        var result = first
        states.forEach { state -> result = merge(result, state) }
        return result
    }
}
