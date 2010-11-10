
function(doc) {
	if (doc.type.lastIndexOf('person', 0) === 0) {

		var subType = doc.type.substring(5);

		// just emit the name
		emit(doc.name, {name:doc.name, subType: subType});
	}
}
