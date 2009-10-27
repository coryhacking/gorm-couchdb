
function(doc) {
    if (doc.type == 'project-task' && doc.completionDate == null) {
        emit('count', 1);
    }
}