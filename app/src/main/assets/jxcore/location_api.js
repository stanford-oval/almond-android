const JavaAPI = require('./java_api');
const LocationJavaAPI = JavaAPI.makeJavaAPI('Location', ['getLocation'], []);

module.exports = {
    getLocation: function(cb) {
        return LocationJavaAPI.getLocation(cb);
    }
};
