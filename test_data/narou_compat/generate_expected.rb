# frozen_string_literal: true
#
# narou.rb の ConverterBase を使ってテストケースの期待出力を生成する
#
# 使い方:
#   ruby test_data/narou_compat/generate_expected.rb
#
# 出力:
#   test_data/narou_compat/expected_narou.txt
#

require "ostruct"
require "stringio"
require "pathname"

# narou gem のライブラリパスを追加
NAROU_GEM_PATH = "C:/Ruby32-x64/lib/ruby/gems/3.2.0/gems/narou-3.8.2/lib"
$LOAD_PATH.unshift(NAROU_GEM_PATH)

# 必要なライブラリを読み込み（narou gem 全体を require）
require "narou"
require "converterbase"

# narou.rb のモジュールをモンキーパッチで上書き
# ConverterBase が参照する最小限の機能のみスタブ化
module Narou
  # global_replace_pattern が root_dir（nil）にアクセスしないよう上書き
  def self.global_replace_pattern
    []
  end

  def self.get_device
    nil
  end
end

# ConverterBase が必要とする設定を OpenStruct でモック
# narou.rb 3.8.2 のデフォルト値を使用
class MockSetting
  attr_accessor :id, :author, :title, :archive_path, :replace_pattern, :settings

  def initialize
    @archive_path = Dir.tmpdir
    @replace_pattern = []
    @settings = {
      "enable_yokogaki"                    => false,
      "enable_inspect"                     => false,
      "enable_convert_num_to_kanji"        => true,
      "enable_kanji_num_with_units"        => true,
      "kanji_num_with_units_lower_digit_zero" => 3,
      "enable_alphabet_force_zenkaku"      => false,
      "disable_alphabet_word_to_zenkaku"   => false,
      "enable_half_indent_bracket"         => true,
      "enable_auto_indent"                 => true,
      "enable_force_indent"                => false,
      "enable_auto_join_in_brackets"       => true,
      "enable_auto_join_line"              => true,
      "enable_enchant_midashi"             => true,
      "enable_author_comments"             => true,
      "enable_erase_introduction"          => false,
      "enable_erase_postscript"            => false,
      "enable_ruby"                        => true,
      "enable_illust"                      => true,
      "enable_transform_fraction"          => false,
      "enable_transform_date"              => false,
      "date_format"                        => "%Y年%m月%d日",
      "enable_convert_horizontal_ellipsis" => true,
      "enable_convert_page_break"          => false,
      "to_page_break_threshold"            => 10,
      "enable_dakuten_font"                => false,
      "enable_display_end_of_book"         => true,
      "enable_pack_blank_line"             => true,
      "enable_kana_ni_to_kanji_ni"         => true,
      "enable_prolonged_sound_mark_to_dash" => false,
      "enable_strip_decoration_tag"        => false,
      "enable_insert_word_separator"       => false,
      "enable_insert_char_separator"       => false,
    }

    # 動的アクセサを定義
    @settings.each_key do |key|
      define_singleton_method(key) { @settings[key] }
      define_singleton_method("#{key}=") { |v| @settings[key] = v }
    end
  end
end

class MockInspector
  attr_writer :messages, :subtitle

  def initialize
    @messages = []
    @subtitle = ""
  end

  def empty?; true; end
  def error?; false; end
  def warning?; false; end
  def info?; false; end
  def inspect_indent(_data); end
  def inspect_end_touten_count(_data); end
  def inspect_brackets(_data); end
  def inspect_line_length(_data); end

  def log(_msg, _klass = nil); end
  def info(_msg); end
  def warning(_msg); end
  def error(_msg); end
end

class MockIllustration
  def initialize(*); end
  def scanner(source, &block); source; end
end

# ---------- メイン処理 ----------

input_file  = File.join(__dir__, "input_cases.txt")
output_file = File.join(__dir__, "expected_narou.txt")

unless File.exist?(input_file)
  abort "ERROR: #{input_file} が見つかりません"
end

# テストケースをパース
cases = []
current_name = nil
current_lines = []

File.readlines(input_file, encoding: "UTF-8").each do |line|
  line.chomp!
  if line =~ /^===CASE:\s*(.+?)\s*===$/
    if current_name
      cases << { name: current_name, input: current_lines.join("\n") }
    end
    current_name = $1
    current_lines = []
  elsif line == "===END==="
    if current_name
      cases << { name: current_name, input: current_lines.join("\n") }
    end
  else
    current_lines << line
  end
end

# ConverterBase を初期化
setting = MockSetting.new
inspector = MockInspector.new
illustration = MockIllustration.new

converter = ConverterBase.new(setting, inspector, illustration)

# 各テストケースを変換して出力
File.open(output_file, "w:UTF-8") do |f|
  cases.each do |tc|
    f.puts "===CASE: #{tc[:name]}==="

    begin
      # text_type="body" で変換（printText に最も近い）
      result = converter.convert(tc[:input], "body")
      f.puts result
    rescue => e
      f.puts "ERROR: #{e.class}: #{e.message}"
      $stderr.puts "WARN: Case '#{tc[:name]}' failed: #{e.message}"
      $stderr.puts e.backtrace.first(5).join("\n") if cases.index(tc) == 0
    end
  end
  f.puts "===END==="
end

puts "Generated: #{output_file}"
puts "Total cases: #{cases.size}"
