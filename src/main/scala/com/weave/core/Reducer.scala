package com.weave.core

import org.apache.pekko.util.Collections

import scala.collection.immutable.{AbstractSeq, LinearSeq}

trait Reducer[S, U] {
  def reduce(state: S, update: U): S
  def merge(left: S, right: S): S
  def merge(first: S, states: S*): S = states.foldLeft(first)(merge)
}
