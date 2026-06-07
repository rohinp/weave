package com.weave.core

trait Reducer[S, U] {
  def reduce(state: S, update: U): S
}