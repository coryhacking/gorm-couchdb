
function(doc) {
    if (doc.type == 'project-task' && doc.completionDate == null) {
        emit(doc.name, 1);
    }
}