# Khởi tạo dự án
mvn clean package

cd D:\Install\kafka_2.13-2.8.0\bin\windows
# Khởi động zookeper và kafka
.\zookeeper-server-start.bat ..\..\config\zookeeper.properties
.\kafka-server-start.bat ..\..\config\server.properties
# Tạo topic đầu vào 
.\kafka-topics.bat --create --topic ATMTXNS --bootstrap-server localhost:9092 --partitions 1 --replication-factor 1
# Kiểm tra danh sách topic
.\kafka-topics.bat --bootstrap-server localhost:9092 --list
# Kiểm tra dữ liệu
.\kafka-console-consumer.bat --bootstrap-server localhost:9092 --topic ATMTXNS --from-beginning

# Test dự án
.\kafka-console-producer.bat --broker-list localhost:9092 --topic ATMTXNS

# Dữ liệu test đúng format
# Test case 1: Phát hiện giao dịch đáng ngờ (2 giao dịch trong 20s)
1001,2001,11000,W/Draw,2025-05-23T17:45:00
1001,2001,12000,W/Draw,2025-05-23T17:45:15

# Test case 2: Giao dịch bình thường (>20s)
1001,2001,11000,W/Draw,2025-05-23T17:45:00
1001,2001,12000,W/Draw,2025-05-23T17:45:30

# Test case 3: Khách hàng khác nhau
1001,2001,11000,W/Draw,2025-05-23T17:45:00
1001,2002,12000,W/Draw,2025-05-23T17:45:15

# Test case 4: Loại giao dịch khác
1001,2001,11000,W/Draw,2025-05-23T17:45:00
1001,2001,12000,DEPOSIT,2025-05-23T17:45:15

# Chạy file ATMCEPKafka.java