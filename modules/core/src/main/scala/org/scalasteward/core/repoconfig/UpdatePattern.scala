/*
 * Copyright 2018-2021 Scala Steward contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scalasteward.core.repoconfig

import cats.Eq
import cats.implicits._
import io.circe.generic.semiauto._
import io.circe.{Codec, Decoder, Encoder}
import org.scalasteward.core.data.{GroupId, Update}

final case class UpdatePattern(
    groupId: GroupId,
    artifactId: Option[String],
    version: Option[UpdatePattern.Version]
) {
  def isWholeGroupIdAllowed: Boolean = artifactId.isEmpty && version.isEmpty
}

object UpdatePattern {
  final case class MatchResult(
      byArtifactId: List[UpdatePattern],
      filteredVersions: List[String]
  )

  final case class Version(
      prefix: Option[String] = None,
      suffix: Option[String] = None,
      exact: Option[String] = None
  ) {
    def matches(version: String): Boolean =
      prefix.forall(version.startsWith) &&
        suffix.forall(version.endsWith) &&
        exact.forall(_ === version)
  }

  def findMatch(
      patterns: List[UpdatePattern],
      update: Update.Single,
      include: Boolean
  ): MatchResult = {
    val byGroupId = patterns.filter(_.groupId === update.groupId)
    val byArtifactId = byGroupId.filter(_.artifactId.forall(_ === update.artifactId.name))
    val filteredVersions = update.newerVersions.filter(newVersion =>
      byArtifactId.exists(_.version.forall(_.matches(newVersion))) === include
    )
    MatchResult(byArtifactId, filteredVersions)
  }

  implicit val updatePatternCodec: Codec[UpdatePattern] =
    deriveCodec

  implicit val updatePatternVersionEq: Eq[Version] =
    Eq.fromUniversalEquals

  implicit val updatePatternVersionDecoder: Decoder[Version] =
    deriveDecoder[Version].or(Decoder[String].map(s => Version(prefix = Some(s))))

  implicit val updatePatternVersionEncoder: Encoder[Version] =
    deriveEncoder
}
