
function(doc) {
    if (doc.type == "project") {
        emit("count", 1);
    }
}
