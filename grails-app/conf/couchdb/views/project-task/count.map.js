
function(doc) {
    if (doc.meta == 'project-task') {
        emit("count", 1);
    }
}
