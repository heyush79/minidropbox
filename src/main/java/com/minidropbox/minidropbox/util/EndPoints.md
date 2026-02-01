Below we have all the end points to test and use this application for all the functionalities

Host-->http://localhost:8080/

1. Register POST /auth/register  with json having email,password
2. Login    POST /auth/login     with json having email, password --> a json web token will generate which to use   for  further operations
3. Upload  POST /api/files/upload
Request:multipart/form-data
Response – 200 OK
{
  "id": 1,
  "originalFilename": "resume.pdf",
  "size": 24576,
  "createdAt": "2026-02-01T10:15:30"
}

4. Download GET /api/files/{id}
Response – 200 OK

File returned as application/octet-stream

Content-Disposition: attachment; filename="file-name.ext"
Errors:
    403 Forbidden – No access
    404 Not Found – File not found

5. List Users Own Files
   GET /api/files

6. Delete file
   DELETE /api/files/{id}

7. Share File with another user
   POST /api/files/{id}/share
   
8. Get Files shared with me
   GET /api/files/shared

9. Revoke File access
   DELETE /api/files/{id}/share

