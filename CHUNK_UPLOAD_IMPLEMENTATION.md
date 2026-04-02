# Chunk-Based Upload Implementation Guide

## Overview
This document details the implementation of **chunk-based file uploads** for the Minidropbox application. This feature enables users to upload large files by breaking them into smaller chunks, supporting resumable uploads, improved reliability, and better progress tracking.

---

## Benefits of Chunk-Based Upload

✅ **Large File Support**: Upload files larger than memory constraints  
✅ **Resumable Uploads**: Resume interrupted uploads from where they stopped  
✅ **Progress Tracking**: Real-time upload progress with percentage completion  
✅ **Reliability**: Network failures only require re-uploading a single chunk, not the entire file  
✅ **Bandwidth Optimization**: Better handling of network interruptions  
✅ **Improved UX**: Users can see progress and estimated time remaining  

---

## Architecture Overview

### New Components

1. **FileChunkMetadata** - JPA Entity
   - Stores metadata about chunked upload sessions
   - Tracks chunk progress and status
   - Links upload session to user

2. **FileChunkRepository** - Spring Data Repository
   - Handles database operations for chunk metadata

3. **ChunkUploadService** - Business Logic Service
   - Core implementation for chunk handling
   - Manages chunk assembly and temporary file cleanup
   - Handles upload lifecycle (init, upload, complete, cancel)

4. **DTOs** - Data Transfer Objects
   - `ChunkUploadInitRequest` - Initialize upload request
   - `ChunkUploadInitResponse` - Initialize upload response with session ID
   - `ChunkStatusResponse` - Current upload progress status

5. **FileController** - REST API Endpoints
   - New chunk upload endpoints
   - Chunk progress monitoring
   - Upload cancellation

---

## Database Schema

### New Table: `file_chunks`

```sql
CREATE TABLE file_chunks (
    id BIGSERIAL PRIMARY KEY,
    upload_id VARCHAR(255) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL REFERENCES users(id),
    original_filename VARCHAR(255) NOT NULL,
    total_size BIGINT NOT NULL,
    chunk_size BIGINT NOT NULL,
    total_chunks INTEGER NOT NULL,
    uploaded_chunks INTEGER NOT NULL DEFAULT 0,
    temp_storage_path TEXT NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    error_message TEXT
);
```

**Fields Description:**

| Field | Type | Description |
|-------|------|-------------|
| `id` | BIGSERIAL | Primary key |
| `upload_id` | VARCHAR(255) | Unique session identifier (UUID) |
| `user_id` | BIGINT | User who initiated upload |
| `original_filename` | VARCHAR(255) | Original file name |
| `total_size` | BIGINT | Total file size in bytes |
| `chunk_size` | BIGINT | Size of each chunk in bytes |
| `total_chunks` | INTEGER | Total number of chunks required |
| `uploaded_chunks` | INTEGER | Number of chunks received so far |
| `temp_storage_path` | TEXT | Temporary directory for storing chunks |
| `status` | VARCHAR(50) | Upload status: IN_PROGRESS, COMPLETED, FAILED, CANCELLED |
| `created_at` | TIMESTAMP | When upload session was created |
| `updated_at` | TIMESTAMP | Last update time |
| `completed_at` | TIMESTAMP | When upload was completed |
| `error_message` | TEXT | Error details if status is FAILED |

---

## API Endpoints

### 1. Initialize Chunked Upload
**Endpoint:** `POST /api/files/chunks/init`

**Purpose:** Initialize a new chunked upload session

**Request Body:**
```json
{
  "originalFilename": "large-video.mp4",
  "totalSize": 157286400,
  "chunkSize": 5242880
}
```

**Response (200 OK):**
```json
{
  "uploadId": "550e8400-e29b-41d4-a716-446655440000",
  "totalChunks": 30,
  "chunkSize": 5242880,
  "message": "Upload session initialized"
}
```

**Error Response (401 Unauthorized):**
- User not authenticated

---

### 2. Upload a Single Chunk
**Endpoint:** `POST /api/files/chunks/upload`

**Purpose:** Upload a individual chunk of the file

**Query Parameters:**
- `uploadId` (required): Upload session ID from initialization
- `chunkNumber` (required): Zero-indexed chunk number (0 to totalChunks-1)

**Request:**
- Multipart form data with file parameter named `chunk`
- Content-Type: `multipart/form-data`

**Example cURL:**
```bash
curl -X POST "http://localhost:8080/api/files/chunks/upload" \
  -H "Authorization: Bearer <token>" \
  -F "uploadId=550e8400-e29b-41d4-a716-446655440000" \
  -F "chunkNumber=0" \
  -F "chunk=@chunk_0.bin"
```

**Response (200 OK):**
```json
{
  "uploadId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "IN_PROGRESS",
  "uploadedChunks": 1,
  "totalChunks": 30,
  "percentageComplete": 3.33,
  "message": "Chunk 0 uploaded successfully"
}
```

**Error Responses:**
- `401 Unauthorized` - User not authenticated
- `404 Not Found` - Upload session not found
- `400 Bad Request` - Invalid chunk number or session not in progress

---

### 3. Get Upload Status
**Endpoint:** `GET /api/files/chunks/{uploadId}/status`

**Purpose:** Check current upload progress

**Path Parameters:**
- `uploadId`: Upload session ID from initialization

**Response (200 OK):**
```json
{
  "uploadId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "IN_PROGRESS",
  "uploadedChunks": 15,
  "totalChunks": 30,
  "percentageComplete": 50.0,
  "message": "Upload in progress"
}
```

**Possible Status Values:**
- `IN_PROGRESS` - Upload session active, chunks being uploaded
- `COMPLETED` - All chunks uploaded and assembled
- `FAILED` - Error occurred during upload
- `CANCELLED` - User cancelled the upload

---

### 4. Complete Chunked Upload
**Endpoint:** `POST /api/files/chunks/complete`

**Purpose:** Finalize the upload by assembling all chunks into the final file

**Query Parameters:**
- `uploadId` (required): Upload session ID

**Validation:**
- All chunks must be uploaded before completion
- Upload session must be in IN_PROGRESS status

**Response (200 OK):**
```json
{
  "id": 42,
  "originalFilename": "large-video.mp4",
  "size": 153406.25,
  "createdAt": "2026-03-31T15:30:45.123456"
}
```

**Error Responses:**
- `400 Bad Request` - Not all chunks uploaded yet
- `404 Not Found` - Upload session not found
- `500 Internal Server Error` - Error during chunk assembly

---

### 5. Cancel Chunked Upload
**Endpoint:** `DELETE /api/files/chunks/{uploadId}/cancel`

**Purpose:** Cancel an ongoing upload and clean up temporary files

**Path Parameters:**
- `uploadId`: Upload session ID to cancel

**Response (200 OK):**
```json
{
  "message": "Upload cancelled successfully"
}
```

**Operations Performed:**
- Marks upload as CANCELLED
- Deletes all temporary chunk files
- Frees up disk space

---

## File Structure

### New Java Files Created

```
src/main/java/com/minidropbox/minidropbox/file/
├── FileChunkMetadata.java          # JPA Entity for chunk metadata
├── FileChunkRepository.java        # JPA Repository
├── ChunkUploadService.java         # Core service logic
└── dto/
    ├── ChunkUploadInitRequest.java  # Request DTO
    ├── ChunkUploadInitResponse.java # Response DTO
    └── ChunkStatusResponse.java     # Status DTO
```

### Modified Files

```
src/main/java/com/minidropbox/minidropbox/file/FileController.java
src/main/resources/application.yml
```

---

## Configuration

### application.yml Updates

```yaml
file:
  upload-dir: "C://Users//Lenovo//minidropbox//minidropbox-storage"
  chunk-temp-dir: "C://Users//Lenovo//minidropbox//minidropbox-storage//chunks"
  chunk-size: 5242880  # 5MB per chunk (default)
  max-chunk-size: 104857600  # 100MB maximum single chunk
  max-concurrent-chunks: 4  # Max concurrent chunks per session
```

**Configuration Properties:**

| Property | Default | Description |
|----------|---------|-------------|
| `upload-dir` | Required | Main upload directory |
| `chunk-temp-dir` | Optional | Temporary directory for chunks (if not set, uses upload-dir/chunks) |
| `chunk-size` | 5MB | Default chunk size in bytes |
| `max-chunk-size` | 100MB | Maximum size for a single chunk |
| `max-concurrent-chunks` | 4 | Maximum concurrent chunk uploads |

---

## Upload Flow Diagram

```
┌─────────────────────────────────────────────────────────────┐
│ 1. CLIENT: Initialize Upload                                │
│    POST /api/files/chunks/init                              │
│    Body: filename, totalSize, chunkSize                     │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│ 2. SERVER: Create Upload Session                            │
│    - Generate unique uploadId                               │
│    - Create temp storage directory                          │
│    - Save FileChunkMetadata to DB                           │
│    - Calculate totalChunks                                  │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼ Return uploadId + totalChunks
┌─────────────────────────────────────────────────────────────┐
│ 3. CLIENT: Upload Chunks (in parallel or sequential)        │
│    FOR each chunk (i = 0 to totalChunks-1):                │
│    POST /api/files/chunks/upload                           │
│    Params: uploadId, chunkNumber                            │
│    Body: chunk binary data                                  │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼ (Repeat for each chunk)
┌─────────────────────────────────────────────────────────────┐
│ 4. SERVER: Store Chunk                                      │
│    - Validate uploadId and ownership                        │
│    - Save chunk to temp directory                           │
│    - Update uploadedChunks count                            │
│    - Return progress status                                 │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼ (Check progress: GET /chunks/{id}/status)
┌─────────────────────────────────────────────────────────────┐
│ 5. CLIENT: All Chunks Uploaded                              │
│    POST /api/files/chunks/complete                          │
│    Params: uploadId                                         │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│ 6. SERVER: Assemble Chunks                                  │
│    - Verify all chunks present                              │
│    - Read chunks in order                                   │
│    - Write to final file location                           │
│    - Create FileMetadata entry                              │
│    - Log audit entry                                        │
│    - Clean up temp chunks                                   │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼ Return FileMetadata
┌─────────────────────────────────────────────────────────────┐
│ 7. CLIENT: Upload Complete                                  │
│    File is now available for download/sharing               │
└─────────────────────────────────────────────────────────────┘
```

---

## Implementation Details

### ChunkUploadService Methods

#### `initializeChunkedUpload()`
```java
public String initializeChunkedUpload(
    String originalFilename,
    Long totalSize,
    Long chunkSize,
    User user) throws IOException
```
- Creates new upload session
- Generates UUID for uploadId
- Calculates total chunks needed
- Creates temporary directory
- Saves FileChunkMetadata to database
- Returns uploadId for client

---

#### `uploadChunk()`
```java
public void uploadChunk(
    String uploadId,
    Integer chunkNumber,
    MultipartFile chunkFile,
    User user) throws IOException
```
- Validates upload session exists and is IN_PROGRESS
- Validates chunk number is within range
- Saves chunk file to temp directory
- Increments uploadedChunks counter
- Updates lastModified timestamp

---

#### `completeChunkedUpload()`
```java
public FileMetadata completeChunkedUpload(
    String uploadId,
    User user) throws IOException
```
- Verifies all chunks have been uploaded
- Creates final file by assembling chunks
- Creates FileMetadata entity
- Logs audit entry
- Updates upload status to COMPLETED
- Cleans up temporary files
- Returns FileMetadata for response

---

#### `assembleChunks()`
```java
private void assembleChunks(
    FileChunkMetadata chunkMetadata,
    Path finalFilePath) throws IOException
```
- Iterates through all chunks in order
- Reads each chunk and writes to final file
- Uses FileInputStream/FileOutputStream for streaming
- Efficient buffer-based approach (8KB buffers)

---

#### `cancelChunkedUpload()`
```java
public void cancelChunkedUpload(
    String uploadId,
    User user) throws IOException
```
- Marks upload as CANCELLED
- Immediately cleans up all temp files
- Frees disk space

---

#### `getUploadStatus()`
```java
public FileChunkMetadata getUploadStatus(
    String uploadId,
    User user)
```
- Retrieves current upload metadata
- Allows progress tracking
- Used by client to get percentage complete

---

### Temporary File Structure

Chunks are stored in a hierarchical structure:
```
${chunk-temp-dir}/
└── chunks/
    └── {uploadId}/
        ├── chunk_0
        ├── chunk_1
        ├── chunk_2
        ...
        └── chunk_n
```

After completion, all files in `{uploadId}/` directory are deleted.

---

## Security Considerations

### 1. User Authorization
- All endpoints require authentication (JWT token)
- Users can only upload to their own account
- Users can only check status of their own uploads
- Users can only cancel their own uploads

### 2. Ownership Verification
```java
FileChunkMetadata chunkMetadata = fileChunkRepository
    .findByUploadIdAndOwner(uploadId, user)
    .orElseThrow(() -> new RuntimeException("Upload session not found"));
```

### 3. Upload Session Validation
- Upload session must exist in database
- Upload session must belong to authenticated user
- Upload session must be IN_PROGRESS status
- Chunk number must be within valid range (0 to totalChunks-1)

### 4. File Storage Security
- Chunks saved with random UUID-based filenames
- Final file renamed with UUID prefix
- Temporary chunks cleaned up immediately after assembly
- No direct path exposure to client

---

## Error Handling

### Error Scenarios and Responses

| Scenario | HTTP Status | Error Message |
|----------|------------|---------------|
| User not authenticated | 401 | User not found |
| Upload session not found | 404 | Upload session not found |
| Not all chunks uploaded | 400 | Not all chunks have been uploaded yet |
| Invalid chunk number | 400 | Invalid chunk number |
| Upload session cancelled | 400 | Upload session is not in progress |
| Assembly fails | 500 | Failed to complete upload: [error details] |
| Insufficient disk space | 500 | I/O error during assembly |

---

## Audit Logging

When a chunked upload is completed, an audit log entry is created:

```
User: {userId}
Action: UPLOAD
FileMetadata: {fileId}
Details: "Uploaded file via chunks: {originalFilename}"
Timestamp: {current time}
```

---

## Performance Considerations

### 1. Chunk Size Selection
- **Smaller chunks (1-5MB)**: Better for unstable networks, slower completion
- **Larger chunks (10-50MB)**: Faster completion, requires stable connection
- **Recommended: 5MB** - Good balance for most scenarios

### 2. Concurrent Uploads
- Multiple chunks from same file can be uploaded concurrently
- Multiple files can be uploaded by different users simultaneously
- Database handles concurrent updates with optimistic locking

### 3. Disk Space
- Temporary chunks removed immediately after assembly
- Only final file persists in main storage
- Monitor disk space if many uploads are in progress

### 4. Assembly Performance
- Linear time complexity O(n) where n = file size
- Streaming approach minimizes memory usage
- Buffer size: 8KB for optimal I/O performance

---

## Testing Scenarios

### Test Case 1: Successful Chunked Upload
```
1. Initialize with 3 chunks of 5MB each (15MB total)
2. Upload chunk 0, 1, 2 sequentially
3. Check status to verify progress
4. Complete upload
5. Verify file exists in storage
6. Verify FileMetadata created
7. Verify audit log entry
```

### Test Case 2: Resumable Upload (Interrupted)
```
1. Initialize upload
2. Upload chunk 0, 1, 2 
3. Upload fails during chunk 3
4. Check status - shows 3/5 complete
5. Resume by uploading chunk 3, 4
6. Complete upload
7. Verify all 5 chunks assembled
```

### Test Case 3: Cancel Upload
```
1. Initialize upload
2. Upload some chunks
3. Cancel upload
4. Verify upload marked as CANCELLED
5. Verify temp files deleted
6. Verify cannot upload more chunks
```

### Test Case 4: Out-of-Order Upload
```
1. Initialize with 5 chunks
2. Upload chunks in random order: 2, 0, 4, 1, 3
3. Complete upload
4. Verify assembly in correct order
5. Verify file integrity
```

---

## Browser/Client Example (JavaScript)

### Using HTML5 File API

```javascript
async function uploadFileInChunks(file) {
  const CHUNK_SIZE = 5 * 1024 * 1024; // 5MB
  const totalChunks = Math.ceil(file.size / CHUNK_SIZE);

  // Step 1: Initialize upload
  const initResponse = await fetch('/api/files/chunks/init', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    },
    body: JSON.stringify({
      originalFilename: file.name,
      totalSize: file.size,
      chunkSize: CHUNK_SIZE
    })
  });

  const { uploadId, totalChunks: calculatedChunks } = await initResponse.json();

  // Step 2: Upload each chunk
  for (let i = 0; i < calculatedChunks; i++) {
    const start = i * CHUNK_SIZE;
    const end = Math.min(start + CHUNK_SIZE, file.size);
    const chunk = file.slice(start, end);

    const formData = new FormData();
    formData.append('uploadId', uploadId);
    formData.append('chunkNumber', i);
    formData.append('chunk', chunk);

    const uploadResponse = await fetch('/api/files/chunks/upload', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`
      },
      body: formData
    });

    const status = await uploadResponse.json();
    console.log(`Uploaded ${status.percentageComplete}%`);
  }

  // Step 3: Complete upload
  const completeResponse = await fetch(`/api/files/chunks/complete?uploadId=${uploadId}`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`
    }
  });

  const fileMetadata = await completeResponse.json();
  console.log('Upload complete!', fileMetadata);
}
```

---

## Maintenance Tasks

### 1. Clean Up Abandoned Chunks
Periodically remove orphaned chunk directories:

```sql
DELETE FROM file_chunks 
WHERE status = 'FAILED' 
AND updated_at < NOW() - INTERVAL '24 hours';
```

### 2. Monitor Disk Space
```bash
# Check chunk temp directory size
du -sh /path/to/minidropbox-storage/chunks

# Find large temp directories
find /path/to/minidropbox-storage/chunks -type d -size +100M
```

### 3. Database Maintenance
```sql
-- Vacuum and analyze file_chunks table
VACUUM ANALYZE file_chunks;

-- Create index on uploadId for faster lookups
CREATE INDEX idx_upload_id ON file_chunks(upload_id);
```

---

## Future Enhancements

1. **Parallel Chunk Upload** - Upload multiple chunks concurrently from client
2. **Chunk Integrity Verification** - MD5/SHA256 checksums per chunk
3. **Pause/Resume** - Extended pause capability with timeout handling
4. **Bandwidth Throttling** - Rate limiting for upload speeds
5. **Chunk Retry Logic** - Automatic retry with exponential backoff
6. **WebSocket Support** - Real-time progress updates via WebSocket
7. **S3 Integration** - Store chunks directly to S3
8. **Compression** - Optional compression of chunks before storage

---

## Troubleshooting

### Problem: Chunks accumulating in temp directory
**Solution:** Check for uploads in FAILED status; implement clean-up job

### Problem: "Not all chunks have been uploaded yet"
**Solution:** Verify all chunks (0 to totalChunks-1) are uploaded; check for network errors

### Problem: File corrupted after assembly
**Solution:** Verify chunk integrity; check for disk space issues during assembly

### Problem: Slow chunk upload
**Solution:** Reduce chunk size; check network bandwidth; verify server disk I/O

---

## Conclusion

The chunk-based upload implementation provides a robust, production-ready solution for handling large file uploads in the Minidropbox application. The feature is secure, scalable, and includes comprehensive error handling and audit logging.

For questions or issues, refer to the relevant code sections or contact the development team.

---

## Appendix: Class Structure

### FileChunkMetadata Entity Structure
```
entity: FileChunkMetadata
├── @Id Long id
├── @Column String uploadId (UNIQUE)
├── @ManyToOne User owner
├── @Column String originalFilename
├── @Column Long totalSize
├── @Column Long chunkSize
├── @Column Integer totalChunks
├── @Column Integer uploadedChunks
├── @Column String tempStoragePath
├── @Column String status
├── @Column LocalDateTime createdAt
├── @Column LocalDateTime updatedAt
├── @Column LocalDateTime completedAt
└── @Column String errorMessage
```

### ChunkUploadService Method Signatures
```java
+ initializeChunkedUpload(filename, size, chunkSize, user): String
+ uploadChunk(uploadId, chunkNumber, file, user): void
+ completeChunkedUpload(uploadId, user): FileMetadata
+ getUploadStatus(uploadId, user): FileChunkMetadata
+ cancelChunkedUpload(uploadId, user): void
- assembleChunks(metadata, path): void
- cleanupChunks(metadata): void
```

---

**Document Version:** 1.0  
**Last Updated:** March 31, 2026  
**Author:** Minidropbox Development Team
