import 'dart:convert';
import 'dart:io';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';
import '../constants/api_constants.dart';

class ApiResponse<T> {
  final bool success;
  final T? data;
  final ApiError? error;
  final Map<String, dynamic>? meta;
  final String? traceId;

  ApiResponse({
    required this.success,
    this.data,
    this.error,
    this.meta,
    this.traceId,
  });

  factory ApiResponse.fromJson(
    Map<String, dynamic> json,
    T Function(dynamic)? fromJsonT,
  ) {
    return ApiResponse<T>(
      success: json['success'] ?? false,
      data: json['data'] != null && fromJsonT != null
          ? fromJsonT(json['data'])
          : json['data'] as T?,
      error: json['error'] != null ? ApiError.fromJson(json['error']) : null,
      meta: json['meta'] as Map<String, dynamic>?,
      traceId: json['traceId'] as String?,
    );
  }
}

class ApiError {
  final String? code;
  final String message;
  final Map<String, dynamic>? fieldErrors;

  ApiError({this.code, required this.message, this.fieldErrors});

  factory ApiError.fromJson(Map<String, dynamic> json) {
    return ApiError(
      code: json['code'] as String?,
      message: json['message'] as String? ?? 'Đã xảy ra lỗi không xác định',
      fieldErrors: json['fieldErrors'] as Map<String, dynamic>?,
    );
  }
}

class ApiClient {
  static final ApiClient _instance = ApiClient._internal();
  factory ApiClient() => _instance;
  ApiClient._internal();

  String _baseUrl = ApiConstants.defaultLocalUrl;
  String? _authToken;

  String get baseUrl => _baseUrl;

  void setBaseUrl(String url) {
    _baseUrl = url;
  }

  Future<void> init() async {
    final prefs = await SharedPreferences.getInstance();
    _authToken = prefs.getString('auth_token');
    final savedUrl = prefs.getString('custom_base_url');
    if (savedUrl != null && savedUrl.isNotEmpty) {
      _baseUrl = savedUrl;
    }
  }

  Future<void> setAuthToken(String? token) async {
    _authToken = token;
    final prefs = await SharedPreferences.getInstance();
    if (token != null) {
      await prefs.setString('auth_token', token);
    } else {
      await prefs.remove('auth_token');
    }
  }

  Future<void> clearAuthToken() async {
    await setAuthToken(null);
  }

  Map<String, String> _buildHeaders() {
    final headers = {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    };
    if (_authToken != null && _authToken!.isNotEmpty) {
      headers['Authorization'] = 'Bearer $_authToken';
    }
    return headers;
  }

  String _formatUrl(String endpoint) {
    final cleanEndpoint = endpoint.startsWith('/') ? endpoint : '/$endpoint';
    return '$_baseUrl$cleanEndpoint';
  }

  Future<ApiResponse<T>> get<T>(
    String endpoint, {
    T Function(dynamic)? parser,
  }) async {
    try {
      final uri = Uri.parse(_formatUrl(endpoint));
      final response = await http
          .get(uri, headers: _buildHeaders())
          .timeout(const Duration(seconds: 5));

      return _handleResponse<T>(response, parser);
    } on SocketException {
      return ApiResponse<T>(
        success: false,
        error: ApiError(
          code: 'NETWORK_ERROR',
          message: 'Không thể kết nối máy chủ. Vui lòng kiểm tra USB reverse hoặc Wi-Fi.',
        ),
      );
    } catch (e) {
      return ApiResponse<T>(
        success: false,
        error: ApiError(code: 'CLIENT_ERROR', message: e.toString()),
      );
    }
  }

  Future<ApiResponse<T>> post<T>(
    String endpoint, {
    Map<String, dynamic>? body,
    T Function(dynamic)? parser,
  }) async {
    try {
      final uri = Uri.parse(_formatUrl(endpoint));
      final response = await http
          .post(
            uri,
            headers: _buildHeaders(),
            body: body != null ? jsonEncode(body) : null,
          )
          .timeout(const Duration(seconds: 8));

      return _handleResponse<T>(response, parser);
    } on SocketException {
      return ApiResponse<T>(
        success: false,
        error: ApiError(
          code: 'NETWORK_ERROR',
          message: 'Không thể kết nối máy chủ. Vui lòng kiểm tra USB reverse hoặc Wi-Fi.',
        ),
      );
    } catch (e) {
      return ApiResponse<T>(
        success: false,
        error: ApiError(code: 'CLIENT_ERROR', message: e.toString()),
      );
    }
  }

  Future<ApiResponse<T>> put<T>(
    String endpoint, {
    Map<String, dynamic>? body,
    T Function(dynamic)? parser,
  }) async {
    try {
      final uri = Uri.parse(_formatUrl(endpoint));
      final response = await http
          .put(
            uri,
            headers: _buildHeaders(),
            body: body != null ? jsonEncode(body) : null,
          )
          .timeout(const Duration(seconds: 8));

      return _handleResponse<T>(response, parser);
    } catch (e) {
      return ApiResponse<T>(
        success: false,
        error: ApiError(code: 'CLIENT_ERROR', message: e.toString()),
      );
    }
  }

  Future<ApiResponse<T>> patch<T>(
    String endpoint, {
    Map<String, dynamic>? body,
    T Function(dynamic)? parser,
  }) async {
    try {
      final uri = Uri.parse(_formatUrl(endpoint));
      final response = await http
          .patch(
            uri,
            headers: _buildHeaders(),
            body: body != null ? jsonEncode(body) : null,
          )
          .timeout(const Duration(seconds: 8));

      return _handleResponse<T>(response, parser);
    } catch (e) {
      return ApiResponse<T>(
        success: false,
        error: ApiError(code: 'CLIENT_ERROR', message: e.toString()),
      );
    }
  }

  Future<ApiResponse<T>> delete<T>(
    String endpoint, {
    Map<String, dynamic>? body,
    T Function(dynamic)? parser,
  }) async {
    try {
      final uri = Uri.parse(_formatUrl(endpoint));
      final response = await http
          .delete(
            uri,
            headers: _buildHeaders(),
            body: body != null ? jsonEncode(body) : null,
          )
          .timeout(const Duration(seconds: 8));

      return _handleResponse<T>(response, parser);
    } catch (e) {
      return ApiResponse<T>(
        success: false,
        error: ApiError(code: 'CLIENT_ERROR', message: e.toString()),
      );
    }
  }

  ApiResponse<T> _handleResponse<T>(
    http.Response response,
    T Function(dynamic)? parser,
  ) {
    if (response.statusCode == 204) {
      return ApiResponse<T>(success: true);
    }

    try {
      final json = jsonDecode(utf8.decode(response.bodyBytes)) as Map<String, dynamic>;
      return ApiResponse.fromJson(json, parser);
    } catch (e) {
      return ApiResponse<T>(
        success: false,
        error: ApiError(
          code: 'PARSE_ERROR',
          message: 'Lỗi định dạng dữ liệu từ máy chủ (HTTP ${response.statusCode})',
        ),
      );
    }
  }
}
