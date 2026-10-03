import redis

r = redis.Redis(host="localhost", port=6379, decode_responses=True)

entries = r.zrange("velocache:traffic:tenant-a:/api/track", 0, -1, withscores=True)

timestamps = [score for member, score in entries]

for i in range(1, len(timestamps)):
    gap = timestamps[i] - timestamps[i - 1]
    print(round(gap))