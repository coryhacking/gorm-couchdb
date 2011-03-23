
function(doc) {
	if (doc.type.lastIndexOf('human', 0) === 0) {

		var subType = doc.type.substring(4);

		// just emit the name
		emit(doc.name, {name:doc.name, subType: subType});
	}
}
