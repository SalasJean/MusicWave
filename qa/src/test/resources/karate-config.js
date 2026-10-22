function fn() {
  var apiBaseUrl = karate.properties['apiBaseUrl'] || 'http://localhost:8080';
  karate.configure('connectTimeout', 5000);
  karate.configure('readTimeout', 10000);
  return { apiBaseUrl: apiBaseUrl };
}
