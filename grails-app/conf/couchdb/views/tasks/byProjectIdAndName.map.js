
function(doc) {
    if (doc.type == 'project-task') {
        emit([doc.projectId, doc.name], null);
    }
}