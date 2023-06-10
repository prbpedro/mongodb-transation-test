docker-compose down

docker-compose up -d

sleep 3
 
docker exec -it mongo mongosh --eval "rs.initiate({_id:'dbrs', members: [{_id:0, host: 'mongo:27017'}]})"

sleep 2

docker exec -it mongo mongosh --eval "load('/scripts/mongo-init.js')"
