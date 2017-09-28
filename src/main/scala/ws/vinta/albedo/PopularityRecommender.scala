package ws.vinta.albedo

import org.apache.spark.sql.SparkSession
import ws.vinta.albedo.evaluators.RankingEvaluator
import ws.vinta.albedo.evaluators.RankingEvaluator._
import ws.vinta.albedo.schemas.{UserItems, UserPopularRepo}
import ws.vinta.albedo.utils.DatasetUtils._

object PopularityRecommender {
  def main(args: Array[String]): Unit = {
    implicit val spark: SparkSession = SparkSession
      .builder()
      .appName("PopularityRecommender")
      .getOrCreate()

    import spark.implicits._

    // Load Data

    val rawUserInfoDS = loadUserInfoDS()
    rawUserInfoDS.cache()

    val rawRepoInfoDS = loadRepoInfoDS()
    rawRepoInfoDS.cache()

    val rawRepoStarringDS = loadRepoStarringDS()
    rawRepoStarringDS.cache()

    // Make Recommendations

    val k = 15

    val popularRepoDF = loadPopularRepoDF().limit(k)
    popularRepoDF.cache()

    val userPopularRepoDS = rawUserInfoDS.select($"user_id")
      .crossJoin(popularRepoDF)
      .as[UserPopularRepo]

    // Evaluate the Model

    val userActualItemsDS = rawRepoStarringDS
      .transform(intoUserActualItems($"user_id", $"repo_id", $"starred_at", k))
      .as[UserItems]

    val userPredictedItemsDS = userPopularRepoDS
      .transform(intoUserPredictedItems($"user_id", $"repo_id", $"stargazers_count".desc))
      .as[UserItems]

    val rankingEvaluator = new RankingEvaluator(userActualItemsDS)
      .setMetricName("ndcg@k")
      .setK(k)
      .setUserCol("user_id")
      .setItemsCol("items")
    val metric = rankingEvaluator.evaluate(userPredictedItemsDS)
    println(s"${rankingEvaluator.getMetricName} = $metric")
    // NDCG@k = 0.0005983585464709745

    spark.stop()
  }
}