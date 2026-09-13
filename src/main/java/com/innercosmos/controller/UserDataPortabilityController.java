package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.export.ExportPackage;
import com.innercosmos.export.UserDataExportService;
import com.innercosmos.export.UserDataImportService;
import com.innercosmos.export.UserDataImportService.ImportReport;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CP-62 data portability endpoints. Export returns the versioned, digest-protected
 * package (format v2); import validates it end-to-end and lands records under the
 * CALLER's account with private-by-default semantics. 退出/迁移不绑购买：no payment or
 * subscription state is consulted on either path.
 */
@RestController
@RequestMapping("/api/me/data")
public class UserDataPortabilityController extends BaseController {

    private final UserDataExportService exports;
    private final UserDataImportService imports;

    public UserDataPortabilityController(UserDataExportService exports,
                                         UserDataImportService imports) {
        this.exports = exports;
        this.imports = imports;
    }

    @GetMapping("/export-package")
    public ApiResponse<ExportPackage> exportPackage(HttpSession session) {
        return ApiResponse.ok(exports.build(currentUserId(session)));
    }

    @PostMapping("/import-package")
    public ApiResponse<ImportReport> importPackage(@RequestBody ExportPackage pkg,
                                                   HttpSession session) {
        return ApiResponse.ok(imports.validateThenImport(currentUserId(session), pkg));
    }
}
