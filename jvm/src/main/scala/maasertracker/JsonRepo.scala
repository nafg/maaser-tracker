package maasertracker

import cats.effect.IO
import io.circe.syntax.*
import io.circe.{Decoder, Encoder}

import java.nio.file.{Files, Path, Paths}

class JsonRepo[A: Decoder: Encoder](path: Path) {
  def this(name: String) = this(Paths.get(s"data/$name.json"))

  def exists = IO.blocking(Files.isRegularFile(path))

  def load =
    IO.blocking {
      this.synchronized {
        Files.createDirectories(path.getParent)
        io.circe.jawn.decodeFile[A](path.toFile).toTry.get
      }
    }

  def update(a: A) =
    IO.blocking {
      this.synchronized {
        Files.createDirectories(path.getParent)
        Files.writeString(path, a.asJson.spaces2)
      }
    }.void

  def modify(f: A => A) = load.map(f).flatMap(update)
}
