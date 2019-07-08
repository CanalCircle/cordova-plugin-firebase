#!/usr/bin/env node

const fs = require('fs');
const version = require('./package.json').version;
console.log(version)
let data = fs.readFileSync('./plugin.xml', "utf8");
data = data.replace(/plugin id="@canalcircle\/cordova-plugin-firebasex-cc" version="[^"]+"/, `plugin id="@canalcircle/cordova-plugin-firebasex-cc" version="${version}"`);
fs.writeFileSync('./plugin.xml', data);
