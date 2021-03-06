package repositories

import com.google.inject.{Inject, Singleton}
import errors.{AlreadyExists, AmbiguousResult, NotFound}
import models.Post
import modules.AppDatabase

import scala.concurrent.{ExecutionContext, Future}

/**
  * An implementation of PostRepository for the Post entity.
  *
  * @param database         the database instance
  * @param executionContext execute program logic asynchronously, typically but not necessarily on a thread pool
  */
@Singleton
class PostRepositoryImpl @Inject()(val database: AppDatabase)
                                  (implicit val executionContext: ExecutionContext) extends PostRepository {

  /**
    * A specific database.
    */
  val db = database.db

  /**
    * A specific database profile.
    */
  val profile = database.profile

  import profile.api._

  def postQuery = TableQuery[Post.Table]

  /**
    * Creates a Post instance.
    *
    * @param post a new Post instance
    * @return the created Post instance
    */
  override def create(post: Post): Future[Post] = db.run {
    Actions.create(post)
  }

  /**
    * Returns an instance found by ID.
    *
    * @param id the Post instance ID
    * @return the found Post instance
    */
  override def find(id: Long): Future[Option[Post]] = db.run {
    Actions.find(id)
  }

  /**
    * Returns a list of Post instances.
    *
    * @return the list of Post instances
    */
  override def findAll(): Future[List[Post]] = db.run {
    Actions.findAll()
  }

  /**
    * Updates an existing Post instance.
    *
    * @param post the new Post instance
    * @return the updated Post instance
    */
  override def update(post: Post): Future[Post] = db.run {
    Actions.update(post)
  }

  /**
    * Deletes an existing Post instance found by ID.
    *
    * @param id the Post instance ID
    * @return the boolean result
    */
  override def delete(id: Long): Future[Option[Post]] = db.run {
    Actions.delete(id)
  }

  /**
    * Provides an implementation for CRUD operations with the Post entity.
    */
  object Actions {

    def create(post: Post): DBIO[Post] =
      for {
        maybePost <- post.id.fold[DBIO[Option[Post]]](DBIO.successful(None))(find)
        maybePostId <- maybePost match {
          case Some(_) => DBIO.failed(AlreadyExists(s"The post with the ID=${post.id} already exists"))
          case _ => postQuery returning postQuery.map(_.id) += post
        }
        maybePost <- find(maybePostId)
        post <- maybePost match {
          case Some(value) => DBIO.successful(value)
          case _ => DBIO.failed(AmbiguousResult(s"Failed to save the post [post=$post]"))
        }
      } yield post


    def find(id: Long): DBIO[Option[Post]] = for {
      maybePost <- postQuery.filter(_.id === id).result
      post <- if (maybePost.lengthCompare(2) < 0) DBIO.successful(maybePost.headOption)
      else DBIO.failed(AmbiguousResult(s"Several posts with the same id = $id"))
    } yield post


    def findAll(): DBIO[List[Post]] = for {
      posts <- postQuery.result
    } yield posts.toList

    def update(post: Post): DBIO[Post] = for {
      maybeId <- post.id.fold[DBIOAction[Long, _, Effect]](DBIO.failed(NotFound(s"Not found 'id' in the [post=$post]")))(DBIO.successful)
      count <- postQuery.filter(_.id === maybeId).update(post)
      result <- count match {
        case 0 => DBIO.failed(NotFound(s"Cannot find a post with the ID=${post.id.get}"))
        case _ => DBIO.successful(post)
      }
    } yield result

    def delete(id: Long): DBIO[Option[Post]] = for {
      maybePost <- find(id)
      maybeDelete <- maybePost match {
        case Some(_) => postQuery.filter(_.id === id).delete
        case _ => DBIO.failed(NotFound(s"Cannot find a post with [ID=$id]"))
      }
      result <- maybeDelete match {
        case 1 => DBIO.successful(maybePost)
        case _ => DBIO.failed(AmbiguousResult(s"Failed to delete the post with the [ID=$id]"))
      }
    } yield result
  }

}
