
function(doc) {
    if (doc.type == "project-task") {
        emit("count", 1);
    }
}
