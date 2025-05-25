package flink.cep.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.PatternSelectFunction;
import org.apache.flink.cep.PatternStream;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.IngestionTimeExtractor;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer08;

public class ATMCEPKafka {

	static final double HIGH_VALUE_TXN = 10000;
	static final long TXN_TIMESPAN_SEC = 20;


	public static void main(String[] args) throws Exception {

		List<String> allTxn = new ArrayList<String>();
		StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
		env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);
		env.getConfig().setAutoWatermarkInterval(1000);


		Properties props = new Properties();
		props.setProperty("zookeeper.connect", "localhost:2181");
		props.setProperty("bootstrap.servers", "localhost:9092");
		props.setProperty("group.id", "test-consumer-group");

		FlinkKafkaConsumer08<ATMFraudEvent> consumer = new FlinkKafkaConsumer08<ATMFraudEvent>(
				"ATMTXNS",                  // Topic name
				new ATMFraudSchema(),       // Schema chuyển đổi
				props						// Kafka properties
		);
		
		DataStream<ATMFraudEvent> ATMTXNStream = env.addSource(consumer).assignTimestampsAndWatermarks(new IngestionTimeExtractor<>());
		


		// FraudAlert pattern: Two consecutive events > 10K ATM withdrawal 
		// appearing within a time interval of 20 seconds

		Pattern<ATMFraudEvent, ?> alertPattern = Pattern.<ATMFraudEvent>begin("first")
				.subtype(ATMFraudEvent.class)
				.where(evt -> evt.getTxnAmount() >= HIGH_VALUE_TXN && 
						evt.getTxnType().equals("W/Draw"))		// Giao dịch 1
				.followedBy("second")
				.subtype(ATMFraudEvent.class)
				.where(evt -> evt.getTxnAmount() >= HIGH_VALUE_TXN && 	// Giao dịch 2
						evt.getTxnType().equals("W/Draw"))		// Trong 20 giây
				.within(Time.seconds(TXN_TIMESPAN_SEC));
		

		// Nhóm theo customerId
		PatternStream<ATMFraudEvent> tempPatternStream = CEP.pattern(
			ATMTXNStream.rebalance().keyBy("customerId"), 
			alertPattern
		);

		// Tạo cảnh báo
		DataStream<ATMFraudAlert> fraudAlerts = tempPatternStream.select(new PatternSelectFunction<ATMFraudEvent, ATMFraudAlert>(){
			private static final long serialVersionUID = 1L;
			@Override
			public ATMFraudAlert select(Map<String, ATMFraudEvent> pattern) {
				ATMFraudEvent first = (ATMFraudEvent) pattern.get("first");
				ATMFraudEvent second = (ATMFraudEvent) pattern.get("second");
				
				LocalDateTime time1 = LocalDateTime.parse(first.getTxnTimeStamp());
				LocalDateTime time2 = LocalDateTime.parse(second.getTxnTimeStamp());
				long secondsBetween = ChronoUnit.SECONDS.between(time1, time2);
				
				if (secondsBetween <= TXN_TIMESPAN_SEC) {
					allTxn.clear();
					allTxn.add(first.getCustomerId() + " made a " + first.getTxnType() + " of " + first.getTxnAmount() + "/- at " + first.getTxnTimeStamp());
					allTxn.add(second.getCustomerId() + " made a " + second.getTxnType() + " of " + second.getTxnAmount() + "/- at " + second.getTxnTimeStamp());
					return new ATMFraudAlert(first.getAtmId(), allTxn);
				}
				return null;
			}
		}).filter(a -> a != null);



		fraudAlerts.print();
		env.execute("CEP monitoring job");
	}

}
