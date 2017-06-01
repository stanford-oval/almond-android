const gettextParser = require('gettext-parser');
const fs = require('fs');

var translation = gettextParser.po.parse(fs.readFileSync(process.argv[2]), 'utf-8');
console.log(JSON.stringify(translation));
