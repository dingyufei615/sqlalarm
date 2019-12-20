package dt.sql.alarm.input
import dt.sql.alarm.exception.SQLAlarmException
import dt.sql.alarm.utils.ConfigUtils
import org.apache.commons.lang3.StringUtils
import org.apache.spark.sql.{Dataset, Row, SparkSession}
import Constants._
import dt.sql.alarm.core.Conf
import dt.sql.alarm.input.Constants.SubscribeType.SubscribeType

/**
  * kafka消息输入
  * Created by songgr on 2019/12/20.
  */
class KafkaInput extends BaseInput {
  @transient private var dStream:Dataset[Row] = _

  override def getDataSetStream(spark: SparkSession): Dataset[Row] = {
    process(spark)
    dStream
  }

  override protected[this] def checkConfig: Option[KafkaConf] = {
    val topic = ConfigUtils.getStringValue(s"$INPUT_PREFIX.$KAFKA_TOPIC")
    val subscribeTypeIndex = ConfigUtils.getIntValue(s"$INPUT_PREFIX.$KAFKA_SUBSCRIBE_TOPIC_PATTERN", 2)
    val servers = ConfigUtils.getStringValue(s"$INPUT_PREFIX.$KAFKA_SERVERS")
    val group = ConfigUtils.getStringValue(s"$INPUT_PREFIX.$KAFKA_GROUP", KAFKA_DEFAULT_GROUP)

    val isValid = StringUtils.isNoneBlank(topic) &&
      StringUtils.isNoneBlank(servers) &&
      StringUtils.isNoneBlank(group)

    if (!isValid) {
      throw new SQLAlarmException(s"$KAFKA_TOPIC and $KAFKA_SERVERS are needed in kafka input conf and cant be empty!")
    }

    if (subscribeTypeIndex <0 || subscribeTypeIndex >2)
      throw new SQLAlarmException(s"$KAFKA_SUBSCRIBE_TOPIC_PATTERN must between 0 and 2. Reference:$SubscribeType")

    Some(KafkaConf(SubscribeType(subscribeTypeIndex), topic, servers, group))
  }

  override protected[this] def process(session: SparkSession) = {
    val conf = checkConfig
    if (conf.isDefined) {
      val kafkaConf = conf.get
      var options = Map("kafka.bootstrap.servers" -> kafkaConf.servers,
        s"${kafkaConf.subscribeType}" -> kafkaConf.topic,
        "group.id" -> kafkaConf.group
      )
      // 默认配置
      options += ("startingOffsets" -> "latest", "max.poll.records" -> "100")
      val lines = session.readStream
        .format("kafka")
        .options(options)
        .load()

      dStream = lines.selectExpr("topic", "CAST(value AS STRING) as value")
    }

  }

  case class KafkaConf(subscribeType:SubscribeType, topic:String, servers:String, group:String) extends Conf
}
