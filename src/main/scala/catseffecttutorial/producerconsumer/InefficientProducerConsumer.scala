/*
 * Copyright (c) 2020 Luis Rodero-Merino
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at.
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package catseffecttutorial.producerconsumer

import cats.effect.{ContextShift, ExitCode, IO, IOApp, Sync}
import cats.effect.concurrent.Ref
import cats.syntax.all._

import collection.immutable.Queue

/**
 * Single producer - single consumer system using an unbounded concurrent queue.
 *
 * Second part of cats-effect tutorial at https://typelevel.org/cats-effect/tutorial/tutorial.html
 */
object InefficientProducerConsumer extends IOApp {

  def producer[F[_]: Sync: ContextShift](queueR: Ref[F, Queue[Int]], counter: Int): F[Unit] =
    (for {
      _ <- if(counter % 10000 == 0) Sync[F].delay(println(s"Produced $counter items")) else Sync[F].unit
      _ <- queueR.getAndUpdate(_.enqueue(counter + 1))
      _ <- ContextShift[F].shift
    } yield ()) >> producer(queueR, counter + 1)

  def consumer[F[_] : Sync: ContextShift](queueR: Ref[F, Queue[Int]]): F[Unit] =
    (for {
      iO <- queueR.modify{ queue =>
        queue.dequeueOption.fold((queue, Option.empty[Int])){case (i,queue) => (queue, Option(i))}
      }
      _ <- if(iO.exists(_ % 10000 == 0)) Sync[F].delay(println(s"Consumed ${iO.get} items"))
        else Sync[F].unit
      _ <- ContextShift[F].shift
    } yield ()) >> consumer(queueR)

  override def run(args: List[String]): IO[ExitCode] =
    for {
      queueR <- Ref.of[IO, Queue[Int]](Queue.empty[Int])
      res <- (consumer(queueR), producer(queueR, 0))
        .parMapN((_, _) => ExitCode.Success) // Run producer and consumer in parallel until done (likely by user cancelling with CTRL-C)
        .handleErrorWith { t =>
          IO(println(s"Error caught: ${t.getMessage}")).as(ExitCode.Error)
        }
    } yield res

}
