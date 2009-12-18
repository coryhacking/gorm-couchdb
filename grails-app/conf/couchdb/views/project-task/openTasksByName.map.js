
function(doc) {
    if (doc.meta == 'project-task' && doc.completionDate == null) {
        emit(doc.name, {name:doc.name, startDate:doc.startDate});
    }
}