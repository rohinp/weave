package com.weave.core

trait StateSchema[S, U]:
  def reducer: Reducer[S, U]