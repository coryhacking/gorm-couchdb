
function(doc) {
    if (doc.type == "project") {

		// emit the doc id and a value... don't "name" the value... 
        emit(doc._id, 1);
    }
}
