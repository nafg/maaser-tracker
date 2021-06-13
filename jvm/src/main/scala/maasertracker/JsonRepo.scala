package maasertracker

import io.circe.syntax._
import io.circe.{Decoder, Encoder}

import java.nio.file.{Files, Path, Paths}

class JsonRepo[A: Decoder: Encoder](path: Path) {
  def this(name: String) = this(Paths.get(s"data/$name.json"))

  def exists = Files.isRegularFile(path)

  def apply() =
    this.synchronized {
      Files.createDirectories(path.getParent)
      io.circe.jawn.decodeFile[A](path.toFile).toTry.get
    }

  def update(a: A) =
    this.synchronized {
      Files.createDirectories(path.getParent)
      Files.writeString(path, a.asJson.spaces2)
    }

  def modify(f: A => A) = update(f(apply()))
}
