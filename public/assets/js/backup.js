t.fileSelected=function(n){if(n&&n.length){var r;t.files={};for(var i in n)r=angular.copy(t.field().uploadInformation()),r.file=n[i],r.url=r.url+t.$parent.entry.values.id,e.upload(r).progress(function(e){t.files[e.config.file.name]={name:e.config.file.name,progress:Math.min(100,parseInt(100*e.loaded/e.total))}}).success(function(e,n,r,i){if(t.files[i.file.name]={name:t.apifilename?e[t.apifilename]:i.file.name,progress:0},t.apifilename){var a=Object.keys(t.files).map(function(e){return t.files[e].name});t.value=a.join(",")}else t.value=Object.keys(t.files).join(",")}).error(function(e,n,r,i){delete t.files[i.file.name],t.value=Object.keys(t.files).join(",")})}},
