
function(doc) {
    if (doc.meta == 'project-task') {
        emit([doc.projectId, doc.name], null);
    }
}